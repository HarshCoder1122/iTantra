# Contributing to iTantra

Thanks for your interest in improving iTantra! Contributions of all sizes are welcome: bug reports, ideas, docs, and code.

By participating you agree to follow our [Code of Conduct](CODE_OF_CONDUCT.md).

## Reporting bugs and requesting features

1. Search [existing issues](https://github.com/HarshCoder1122/iTantra/issues) first.
2. If nothing matches, open a new issue with:
   - what you expected vs. what happened,
   - steps to reproduce,
   - your OS and tool versions,
   - screenshots or logs where helpful.

For security problems, do **not** open a public issue. See [SECURITY.md](SECURITY.md).

## Development workflow

1. Fork the repository and clone your fork.
2. Create a branch from `main`: `git checkout -b feat/short-description`.
3. Open the project in Android Studio, make your change, then run `./gradlew test` and try it on a device or emulator.
4. Commit with a clear message in the imperative mood (for example `Fix quantity validation`).
5. Push your branch and open a pull request describing **what** changed and **why**.

## Pull request checklist

- [ ] The change is focused; unrelated cleanups go in a separate PR.
- [ ] It runs locally and does not break existing behavior.
- [ ] No secrets, API keys, or personal data are committed.
- [ ] Docs and the README are updated if behavior changed.

## License

By contributing, you agree that your contributions will be licensed under the [MIT License](LICENSE).
