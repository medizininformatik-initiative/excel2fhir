# Repository Notes for Codex

## Project

- The GitHub repository is `medizininformatik-initiative/excel2fhir`.
- The main integration branch for current work is `develop`.
- Use `codex/` as the branch prefix for Codex-created branches.
- Prefer small, reviewable pull requests.

## Build and Validation

- Use Maven for local validation.
- Run `mvn test` for normal code and documentation-only changes.
- Run `mvn test package` when dependency, packaging, Docker image, or shaded JAR contents may be affected.
- CI runs CodeQL in the `test` job and Trivy in the `security-scan` job.
- After merging security-related PRs, wait for the push CI run on `develop` before trusting the GitHub code-scanning alert list. Alerts can remain stale until the new SARIF upload finishes.

## Formatting

- Java formatting is defined by the committed Eclipse settings in `.settings/org.eclipse.jdt.core.prefs` and `.settings/org.eclipse.jdt.ui.prefs`.
- Keep formatting-only changes in their own commit or PR when possible.
- Avoid committing local IDE metadata beyond the explicitly tracked `.settings` files.

## GitHub Workflow

- The local environment uses `gh` for GitHub actions such as creating PRs, checking CI, and updating issues.
- For PR checks, `gh pr checks <number> --repo medizininformatik-initiative/excel2fhir` gives the concise status.
- For code scanning, query `refs/heads/develop` when verifying the branch state:

```sh
gh api 'repos/medizininformatik-initiative/excel2fhir/code-scanning/alerts?ref=refs/heads/develop&state=open&per_page=100'
```

## Security Alert Context

- The previous GitHub Advanced Security cleanup was tracked in issue #29.
- Security/code-scanning fixes were merged through PRs #30, #31, #32, and #33.
- As of the `develop` CI run for commit `80d29ce`, code scanning reported `0` open alerts.

