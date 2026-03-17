# Interview Q&A Matrix (SRE / Platform / DevSecOps) / 面试问答矩阵（SRE / Platform / DevSecOps）

> Same question, three role-specific answer angles.  
> 同一个问题，三种岗位化答法。

---

## Q1: Tell me about this project in one sentence.
## Q1：用一句话介绍这个项目。

| Role | EN Answer | 中文回答 |
|---|---|---|
| SRE | I turned an AI-accelerated EKS delivery into a reliability-first system with failure-mode validation and recovery discipline. | 我把 AI 加速的 EKS 交付升级为“可靠性优先”的系统，重点是故障模式验证和恢复纪律。 |
| Platform | I standardized this project into reusable Terraform/Helm/CI contracts so delivery behavior is consistent across environments. | 我把这个项目标准化为可复用的 Terraform/Helm/CI 契约，让多环境交付行为保持一致。 |
| DevSecOps | I built an enforceable security chain from scan to signed-image admission, so risk is blocked before production. | 我搭建了从扫描到签名准入的可强制安全链路，把风险阻断在生产之前。 |

---

## Q2: What was your biggest technical challenge?
## Q2：你遇到的最大技术挑战是什么？

| Role | EN Answer | 中文回答 |
|---|---|---|
| SRE | Hidden defects only appeared in fresh rebuilds, so I made `destroy + apply` validation mandatory. | 隐性缺陷只在全新重建时暴露，所以我把 `destroy + apply` 验证设为必选。 |
| Platform | Keeping configuration consistent across docs, scripts, charts, and pipeline without drift was the hardest part. | 最难的是在文档、脚本、Chart、流水线之间保持一致并避免漂移。 |
| DevSecOps | Converting security from advisory checks to release-blocking enforcement without breaking delivery flow. | 最难的是把安全从建议检查升级为阻断门禁，同时不破坏交付流。 |

---

## Q3: How did you ensure production reliability?
## Q3：你如何保障生产可靠性？

| Role | EN Answer | 中文回答 |
|---|---|---|
| SRE | I enforced deploy-verify-observe-recover loops with clear rollback triggers and runtime evidence. | 我建立了部署-验证-观测-恢复闭环，并定义了明确回滚触发和运行证据。 |
| Platform | I used standardized release paths and values-driven config to eliminate ad-hoc operational variance. | 我用标准化发布路径和 values 驱动配置，消除临时性运维差异。 |
| DevSecOps | Reliability included trust: only verified artifacts could run, reducing risky unknowns in production. | 可靠性包含“可信运行”：只有通过验证的制品才能运行，从源头降低未知风险。 |

---

## Q4: How did you use AI responsibly?
## Q4：你如何负责任地使用 AI？

| Role | EN Answer | 中文回答 |
|---|---|---|
| SRE | AI wrote drafts; I validated runtime behavior under normal and failure paths before accepting changes. | AI 负责草稿；我在成功和故障路径下验证运行行为后才接受改动。 |
| Platform | AI accelerated documentation and scaffolding, but interfaces and standards were human-owned and enforced. | AI 加速文档和脚手架，但接口与标准由人工定义并强制执行。 |
| DevSecOps | AI proposals had to pass policy gates, not just look correct in code review. | AI 方案必须通过策略门禁，而不是只在代码评审里“看起来正确”。 |

---

## Q5: What measurable impact did you deliver?
## Q5：你交付了哪些可量化影响？

| Role | EN Answer | 中文回答 |
|---|---|---|
| SRE | We reduced hidden release risk by validating fresh-state rebuild behavior and tightening rollback confidence. | 通过验证全新重建行为并增强回滚信心，我们降低了隐性发布风险。 |
| Platform | We improved onboarding and handoff by converting scattered knowledge into standardized runbooks and scripts. | 通过把分散知识标准化为 runbook 和脚本，我们提升了上手与交接效率。 |
| DevSecOps | We improved supply-chain trust by enforcing scan/sign/admission flow before promotion. | 通过在晋级前强制执行扫描/签名/准入流程，我们提升了供应链可信度。 |

---

## Q6: How did you handle trade-offs between speed and safety?
## Q6：你如何平衡速度与安全？

| Role | EN Answer | 中文回答 |
|---|---|---|
| SRE | Speed came from automation; safety came from runtime validation and rollback discipline. | 速度来自自动化，安全来自运行验证和回滚纪律。 |
| Platform | I optimized for repeatability first, then speed; stable templates scale faster than heroic fixes. | 我优先保证可重复，再追求速度；稳定模板比临场救火更能扩展。 |
| DevSecOps | Fast changes are allowed only if security controls remain enforceable and auditable. | 只有在安全控制仍可强制、可审计时，快速变更才被允许。 |

---

## Q7: Tell me about a failure and what you learned.
## Q7：说一个失败案例和你的复盘。

| Role | EN Answer | 中文回答 |
|---|---|---|
| SRE | A deploy path looked healthy in incremental runs but failed in fresh rebuild; I learned to distrust state-carrying success. | 某部署路径在增量环境看似正常，但全新重建失败；我学到不能信任“带状态成功”。 |
| Platform | Documentation and implementation drifted; I learned to treat docs as operational interfaces, not passive notes. | 文档与实现出现漂移；我学到文档必须当作运维接口而不是被动说明。 |
| DevSecOps | Security checks were initially informational; I learned enforcement beats awareness in production risk control. | 安全检查最初只是提示；我学到在生产风险控制中，“强制”比“提醒”更有效。 |

---

## Q8: How do you prioritize work during incidents?
## Q8：故障时你如何排优先级？

| Role | EN Answer | 中文回答 |
|---|---|---|
| SRE | I prioritize by blast radius, user impact, and recovery reversibility. | 我按影响半径、用户影响和恢复可逆性排优先级。 |
| Platform | I stabilize shared platform dependencies first, then service-specific optimizations. | 我先稳定共享平台依赖，再做服务级优化。 |
| DevSecOps | I first contain active risk exposure, then restore secure service continuity. | 我先收敛正在暴露的风险，再恢复安全前提下的服务连续性。 |

---

## Q9: Why should we hire you for this role?
## Q9：为什么我们应该录用你？

| Role | EN Answer | 中文回答 |
|---|---|---|
| SRE | I bring reliability judgment under pressure, not just tooling familiarity. | 我带来的是高压场景下的可靠性判断力，而不只是工具熟练度。 |
| Platform | I can convert team knowledge into reusable platform standards that scale. | 我能把团队经验沉淀为可扩展的平台标准。 |
| DevSecOps | I can make security practical: enforceable, observable, and delivery-compatible. | 我能让安全“可落地”：可强制、可观测、与交付兼容。 |

---

## Q10: What would you improve next if given more time?
## Q10：如果再给你时间，你会继续优化什么？

| Role | EN Answer | 中文回答 |
|---|---|---|
| SRE | I would expand game-day style failure drills and automate recovery validation further. | 我会扩展 game-day 故障演练，并进一步自动化恢复验证。 |
| Platform | I would package more reusable modules and enforce stronger interface versioning across teams. | 我会封装更多可复用模块，并强化跨团队接口版本治理。 |
| DevSecOps | I would deepen policy-as-code coverage and shorten security feedback loops in CI. | 我会扩大 policy-as-code 覆盖范围，并缩短 CI 安全反馈回路。 |

---

## Quick Usage Tips / 快速使用建议

1. EN: Pick one primary role narrative and one backup narrative.
1. 中文：选一个主岗位叙事，再准备一个备选叙事。
2. EN: Keep each answer under 45 seconds, then add evidence if asked deeper.
2. 中文：每个回答控制在 45 秒内，追问时再补证据。
3. EN: Always end with one concrete artifact (script, policy, pipeline, or runtime check).
3. 中文：每次回答最后都落到一个具体制品（脚本、策略、流水线或运行验证）。
