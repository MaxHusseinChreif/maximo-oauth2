# OpenShift resources

`app.yaml` is the reviewable baseline for the OpenShift build and runtime resources. It intentionally contains no Secret values or environment-specific Route host.

Before applying it, replace `MAXIMO_SCRIPT_BASE_URL` in `maximo-oauth2-config` and confirm these same-project resources already exist:

- Secret `maximo-oauth2-runtime` with `OAUTH_CLIENT_ID`, `OAUTH_CLIENT_SECRET`, and `MAXIMO_API_KEY`.
- Secret `maximo-oauth2-jwt` with `private-key.pem` and `public-key.pem`.
- ConfigMap `maximo-ca-cert` with the verified Maximo issuing CA under key `ca.crt`.

The private key and Maximo API key must never be committed. The previously exposed values must be rotated before rollout.

The init container copies the JVM default truststore before importing the Maximo CA. This preserves public CA trust. After rollout, confirm its log contains alias `maximo-public-ca`, then make a successful Maximo request from the application.

Use the OpenShift console YAML editor to review and apply the resources. Preserve any approved environment-specific Route host, replica count, resource sizing, or organizational labels when reconciling this baseline with an existing project.

The TCP probes validate process availability without exposing a public unauthenticated health endpoint. Tune resource values from observed production metrics.

Configure request-rate limiting for `/api/token` in the approved ingress, API gateway, or web application firewall. Do not use an in-memory limiter when more than one pod can serve traffic.
