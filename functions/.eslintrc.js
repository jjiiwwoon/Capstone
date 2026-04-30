module.exports = {
  root: true,
  env: { node: true, es2020: true },
  parserOptions: {
    ecmaVersion: 2020,
    sourceType: "script", // CommonJS 사용이므로 'script' 권장
  },
  extends: ["eslint:recommended"],
  rules: {},
  ignorePatterns: ["node_modules/**", "lib/**"],
};
