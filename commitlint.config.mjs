// MCMORY 커밋 컨벤션: Conventional Commits `{type}({scope}): {description}`.
// campus는 gitmoji 접두사를 강제했으나 MCMORY는 채택하지 않았다(AGENTS.md 6장).
// 팀이 gitmoji로 합의하면 headerPattern에 이모지 그룹을 추가한다.
// wagoid/commitlint-github-action v6는 .js 설정을 지원하지 않으므로 .mjs 확장자 필수.
const config = {
  rules: {
    "type-enum": [
      2,
      "always",
      ["feat", "fix", "docs", "test", "refactor", "chore", "style", "perf", "ci", "revert"],
    ],
    "type-case": [2, "always", "lower-case"],
    "type-empty": [2, "never"],
    "subject-empty": [2, "never"],
    // description은 소문자 시작, 마침표 없이(AGENTS.md 6장)
    "subject-full-stop": [2, "never", "."],
    "header-max-length": [2, "always", 100],
  },
};

export default config;
