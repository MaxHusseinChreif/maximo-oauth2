# maximo-oauth2

Spring Boot proxy service that issues RS256 bearer tokens and forwards authenticated requests to Maximo scripts.

## Required runtime configuration

The application fails at startup when required credentials or signing keys are missing or invalid.

| Setting | Purpose | Recommended OpenShift source |
| --- | --- | --- |
| `OAUTH_CLIENT_ID` | Token endpoint client ID | Secret |
| `OAUTH_CLIENT_SECRET` | Token endpoint client secret | Secret |
| `MAXIMO_SCRIPT_BASE_URL` | HTTPS URL ending in `/maximo/api/script` | ConfigMap or environment value |
| `MAXIMO_API_KEY` | Maximo API key | Secret |
| `JWT_PRIVATE_KEY_LOCATION` | Spring resource location of a PKCS#8 RSA private PEM | Mounted Secret file |
| `JWT_PUBLIC_KEY_LOCATION` | Spring resource location of an X.509 RSA public PEM | Mounted Secret file |

The JWT location settings default to:

```text
file:/etc/maximo-oauth2/jwt/private-key.pem
file:/etc/maximo-oauth2/jwt/public-key.pem
```

The RSA pair must match and contain keys of at least 2048 bits. Use one shared key pair for every replica so tokens remain valid across pods and restarts.

## Generate the JWT signing keys

Generate the key pair once in a protected administrative environment:

```powershell
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out private-key.pem
openssl pkey -in private-key.pem -pubout -out public-key.pem
```

Protect `private-key.pem`; never commit it or copy it into the container image.

## OpenShift configuration

Create the runtime and JWT secrets in the same project as the DeploymentConfig:

```powershell
oc create secret generic maximo-oauth2-runtime `
  --from-literal=OAUTH_CLIENT_ID='<client-id>' `
  --from-literal=OAUTH_CLIENT_SECRET='<strong-random-secret>' `
  --from-literal=MAXIMO_API_KEY='<rotated-maximo-api-key>'

oc create secret generic maximo-oauth2-jwt `
  --from-file=private-key.pem=private-key.pem `
  --from-file=public-key.pem=public-key.pem
```

Commands containing literal secrets can be retained in PowerShell history. Prefer the OpenShift web console or an approved secrets manager when that is a concern.

Attach the runtime secret and non-secret URL to the DeploymentConfig:

```powershell
oc set env --from=secret/maximo-oauth2-runtime dc/<deploymentconfig-name>
oc set env dc/<deploymentconfig-name> MAXIMO_SCRIPT_BASE_URL='https://<maximo-host>/maximo/api/script'
```

Mount the JWT Secret on the DeploymentConfig pod template:

```yaml
spec:
  template:
    spec:
      containers:
        - name: <container-name>
          volumeMounts:
            - name: jwt-keys
              mountPath: /etc/maximo-oauth2/jwt
              readOnly: true
      volumes:
        - name: jwt-keys
          secret:
            secretName: maximo-oauth2-jwt
```

Do not add these values to the BuildConfig. They are runtime configuration and belong on the DeploymentConfig pod template.

The previously committed Maximo API key must be revoked and replaced before deployment because removing it from the current source does not remove it from Git history.

## Redeploy

After the secrets, environment values, and volume are attached:

```powershell
oc start-build <buildconfig-name> --follow
oc rollout latest dc/<deploymentconfig-name>
oc rollout status dc/<deploymentconfig-name>
```

ConfigChange or ImageChange triggers might start the rollout automatically. Check the DeploymentConfig status before starting a duplicate rollout.

## TLS trust

Outbound Maximo connections use TLS 1.2 or TLS 1.3 with the JVM's normal certificate-chain and hostname verification. If the Maximo endpoint uses a private certificate authority, add that CA to an approved Java truststore and mount the truststore into the pod. Do not restore a trust-all certificate manager or permissive hostname verifier.

## Local Docker Compose

Copy `.env.example` to `.env`, replace every placeholder, generate the signing keys under `secrets/`, then run:

```powershell
docker compose up --build
```

Both `.env` and `secrets/` are ignored by Git.
