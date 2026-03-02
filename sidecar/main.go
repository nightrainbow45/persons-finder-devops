// PII Redaction Sidecar Proxy
// Intercepts outbound LLM requests, redacts PII before forwarding,
// and restores tokens in responses. Acts as Layer 2 defense-in-depth
// behind the in-process PiiProxyService (Layer 1).
//
// Flow:
//   main app → localhost:8081 (this proxy) → api.openai.com
//
// When Layer 1 already redacted the request body, this proxy finds no PII
// and forwards transparently. When a request bypasses Layer 1 entirely,
// this proxy catches and redacts any raw PII before it leaves the cluster.
package main

import (
	"bytes"
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"regexp"
	"strings"
	"time"
)

// Regex patterns mirror PiiDetector.kt in the main application.
// Keeping them in sync ensures consistent detection across both layers.
var (
	// Matches "First Last" or "First Middle Last" capitalized patterns
	namePattern = regexp.MustCompile(`\b[A-Z][a-z]+(?:\s+[A-Z][a-z]+)+\b`)
	// Matches latitude/longitude decimal numbers: -33.8688, 151.2093
	coordPattern = regexp.MustCompile(`-?\d{1,3}\.\d{1,15}`)
)

var (
	listenPort  = getenv("LISTEN_PORT", "8081")
	upstreamURL = getenv("UPSTREAM_URL", "https://api.openai.com")
)

func getenv(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}

// randomHex returns n random hex bytes (2n characters).
func randomHex(n int) string {
	b := make([]byte, n)
	_, _ = rand.Read(b)
	return hex.EncodeToString(b)
}

type redactionResult struct {
	body     string
	tokenMap map[string]string // token → original PII value
	piiTypes []string
}

// redact scans text for PII, replaces matches with reversible tokens.
// Processes names before coordinates to avoid partial matches.
func redact(text string) redactionResult {
	tokenMap := make(map[string]string)
	seen := make(map[string]bool) // original value → already tokenized
	piiSet := make(map[string]bool)
	result := text

	// Step 1: tokenize person names
	for _, match := range uniqueStrings(namePattern.FindAllString(text, -1)) {
		if seen[match] {
			continue
		}
		token := "<NAME_" + randomHex(4) + ">"
		tokenMap[token] = match
		seen[match] = true
		result = strings.ReplaceAll(result, match, token)
		piiSet["PERSON_NAME"] = true
	}

	// Step 2: tokenize coordinates (scan result after name replacement)
	for _, match := range uniqueStrings(coordPattern.FindAllString(result, -1)) {
		// Skip anything inside an existing token (e.g. numbers inside <NAME_...>)
		if strings.ContainsAny(match, "<>") {
			continue
		}
		if seen[match] {
			continue
		}
		token := "<COORD_" + randomHex(4) + ">"
		tokenMap[token] = match
		seen[match] = true
		result = strings.ReplaceAll(result, match, token)
		piiSet["COORDINATE"] = true
	}

	piiTypes := make([]string, 0, len(piiSet))
	for k := range piiSet {
		piiTypes = append(piiTypes, k)
	}

	return redactionResult{body: result, tokenMap: tokenMap, piiTypes: piiTypes}
}

// restore replaces all tokens in text with their original PII values.
func restore(text string, tokenMap map[string]string) string {
	for token, original := range tokenMap {
		text = strings.ReplaceAll(text, token, original)
	}
	return text
}

func uniqueStrings(ss []string) []string {
	seen := make(map[string]bool, len(ss))
	out := make([]string, 0, len(ss))
	for _, s := range ss {
		if !seen[s] {
			seen[s] = true
			out = append(out, s)
		}
	}
	return out
}

// auditEntry mirrors AuditLogEntry.kt so logs are consistent across layers.
type auditEntry struct {
	Type              string   `json:"type"`
	Timestamp         string   `json:"timestamp"`
	RequestID         string   `json:"requestId"`
	PiiDetected       []string `json:"piiDetected"`
	RedactionsApplied int      `json:"redactionsApplied"`
	Destination       string   `json:"destination"`
	Method            string   `json:"method"`
	Layer             string   `json:"layer"`
}

func logAudit(requestID, destination, method string, piiTypes []string, redactions int) {
	types := piiTypes
	if types == nil {
		types = []string{}
	}
	entry := auditEntry{
		Type:              "PII_AUDIT",
		Timestamp:         time.Now().UTC().Format(time.RFC3339),
		RequestID:         requestID,
		PiiDetected:       types,
		RedactionsApplied: redactions,
		Destination:       destination,
		Method:            method,
		Layer:             "sidecar-layer2",
	}
	b, _ := json.Marshal(entry)
	log.Println(string(b))
}

var httpClient = &http.Client{Timeout: 60 * time.Second}

func proxyHandler(w http.ResponseWriter, r *http.Request) {
	requestID := randomHex(8)

	body, err := io.ReadAll(r.Body)
	if err != nil {
		http.Error(w, "failed to read request body", http.StatusBadRequest)
		return
	}
	defer r.Body.Close()

	// Redact PII from request body
	redacted := redact(string(body))

	// Build upstream URL: base + original path + query
	targetURL := strings.TrimRight(upstreamURL, "/") + r.URL.Path
	if r.URL.RawQuery != "" {
		targetURL += "?" + r.URL.RawQuery
	}

	upstreamReq, err := http.NewRequest(r.Method, targetURL, bytes.NewBufferString(redacted.body))
	if err != nil {
		http.Error(w, "failed to create upstream request", http.StatusInternalServerError)
		return
	}

	// Forward all headers unchanged (includes Authorization: Bearer <key>)
	for key, values := range r.Header {
		for _, v := range values {
			upstreamReq.Header.Add(key, v)
		}
	}

	resp, err := httpClient.Do(upstreamReq)
	if err != nil {
		http.Error(w, "upstream request failed: "+err.Error(), http.StatusBadGateway)
		return
	}
	defer resp.Body.Close()

	respBody, err := io.ReadAll(resp.Body)
	if err != nil {
		http.Error(w, "failed to read upstream response", http.StatusInternalServerError)
		return
	}

	// Restore any tokens we introduced back to original PII values
	restored := restore(string(respBody), redacted.tokenMap)

	logAudit(requestID, targetURL, r.Method, redacted.piiTypes, len(redacted.tokenMap))

	for key, values := range resp.Header {
		for _, v := range values {
			w.Header().Add(key, v)
		}
	}
	w.WriteHeader(resp.StatusCode)
	fmt.Fprint(w, restored)
}

func main() {
	mux := http.NewServeMux()

	mux.HandleFunc("/health", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		fmt.Fprint(w, `{"status":"ok","layer":"pii-sidecar"}`)
	})

	mux.HandleFunc("/", proxyHandler)

	addr := ":" + listenPort
	log.Printf(`{"msg":"PII redaction sidecar starting","listenPort":%q,"upstreamURL":%q}`, listenPort, upstreamURL)
	if err := http.ListenAndServe(addr, mux); err != nil {
		log.Fatalf("server error: %v", err)
	}
}
