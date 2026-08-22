// pm2 process definition — matches InterSegoService's deployment pattern (see
// Features/backend-data-service.md's "Framework/stack" section).
module.exports = {
  apps: [
    {
      name: "PharmaciasDataService",
      script: "dist/index.js",
      cwd: __dirname,
      env: {
        NODE_ENV: "production",
      },
    },
  ],
};
