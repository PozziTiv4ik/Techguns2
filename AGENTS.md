# Project instructions

## Continuing the port

- This repository carries the working context across chats. For a new development
  chat or a request such as "продолжай", read [the handoff](docs/HANDOFF.ru.md)
  and [the development workflow](docs/DEVELOPMENT_WORKFLOW.ru.md). Then inspect
  Git and the relevant source files; do not rely on access to an earlier chat.
- "Продолжай" authorizes the next coherent porting milestone from the handoff,
  including implementation, necessary downloads/build tools, local headless tests,
  fixes, commits, pushes to the existing fork and verification of GitHub CI.
  Continue through those steps without asking the user to choose routine details.
  An explicit narrower request takes precedence; a status question alone is not
  authorization to start a new milestone.
- Finish a usable, verified milestone before reporting completion. Resolve failing
  checks before starting another feature. If an external blocker prevents finishing,
  record the exact remaining work and evidence in the handoff and report it honestly.
- Read only the relevant sections of the plan/status/verification history. The
  handoff holds the next task; Git, source and actual test results resolve stale notes.

## Finding the right code

- Use [the code map](docs/CODE_MAP.ru.md) to select a subsystem, shared entry
  points and tests. Use [the topic index](docs/README.ru.md) for feature behavior;
  do not read the entire verification history to locate an implementation.
- Before editing catalogs, resources or generated Java, find the owning input
  or converter in [the generator map](tools/README.ru.md). `content/weapon-ports.json`
  is an input; many other content and Java files are outputs. Change the owner
  and regenerate instead of patching its output alone.
- When moving an entry point or adding a subsystem/converter, update the relevant
  map row. Keep the next task in the handoff and test evidence in verification;
  do not duplicate their changing values in navigation maps.

## Boundaries

- Do not use Computer Use, native UI automation, keyboard/mouse injection, or take
  control of the user's desktop. Do not open, activate or manipulate application windows.
- Do not launch interactive Minecraft clients (`runClient`) on this machine.
  Use source inspection, shell commands, builds, headless GameTests and GitHub APIs.
- Preserve the original Techguns license and attribution. Do not publish mod releases
  or binary downloads without the original authors' permission.
- The target is Minecraft 26.2 / NeoForge. Other platforms are planned candidates,
  not supported versions. Do not claim the full port is finished from a partial feature.
- Keep `legacy/1.12.2` unchanged as a reference. Modern compilation must not include it.
- Preserve unrelated user changes. Do not reset, clean, force-push or rewrite the
  published history to make a checkpoint look clean.

## Verification and publication

- Use the existing `port/26.2-neoforge` branch when it is the active continuation
  branch. The writable fork is `PozziTiv4ik/Techguns2` (`origin`); `upstream` is
  reference-only. Explicitly pass `--repo PozziTiv4ik/Techguns2` to `gh` commands
  that accept it. Scope `gh api` paths/filters to the same repository.
- A normal implementation milestone has two meaningful commits and two pushes:
  tested source/content/docs first, then a documentation-only CI checkpoint with
  `[skip ci]`. Verify CI against the exact source commit before recording success.
  This is a workflow, not a quota: fixes can need more commits; documentation-only
  tasks can need fewer. Never create empty commits or skip failures to reach two.
- Follow the workflow's checks for code/content changes. For documentation-only
  work, validate links, commands, consistency and the diff; do not rerun Minecraft
  just to repeat unchanged results. Do not call an old passing run a new test run.
- Keep `docs/HANDOFF.ru.md`, `docs/STATUS.ru.md`, `docs/PORTING_PLAN.ru.md` and
  `docs/VERIFICATION.ru.md` consistent with actual work. Update affected sections
  and replace the handoff's current state/next task at each milestone.

## Communication

- Write in Russian, plainly and briefly. Start with the next concrete action;
  report meaningful progress during longer work.
- The final report states what became usable, what actually passed, the CI link
  when applicable, actual commits/pushes in this turn, material remaining limits
  and the next milestone. Do not promise a percentage based on test/file counts.
- Do not create other chats, change the selected model, spawn subagents or install
  plugins just to continue the port. These rules work with the user's selected model.
