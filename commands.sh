curl --location 'http://localhost:8081/realms/triagedeck/protocol/openid-connect/token' \
--header 'Content-Type: application/x-www-form-urlencoded' \
--data-urlencode 'grant_type=password' \
--data-urlencode 'client_id=triagedeck-spa' \
--data-urlencode 'username=operator' \
--data-urlencode 'password=operator'

curl --location 'http://localhost:8081/realms/triagedeck/protocol/openid-connect/token' \
--header 'Content-Type: application/x-www-form-urlencoded' \
--data-urlencode 'grant_type=client_credentials' \
--data-urlencode 'client_id=ingest-service' \
--data-urlencode 'client_secret=Br772UsZFYYEntsV9Y0yz4lfPFAlP5Jw'

---------

AUTH_TOKEN_RESP=$(curl --location 'http://localhost:8081/realms/triagedeck/protocol/openid-connect/token' \
--header 'Content-Type: application/x-www-form-urlencoded' \
--data-urlencode 'grant_type=client_credentials' \
--data-urlencode 'client_id=ingest-service' \
--data-urlencode 'client_secret=Br772UsZFYYEntsV9Y0yz4lfPFAlP5Jw')

# 打印完整返回
# echo "$AUTH_TOKEN_RESP"

# 如果只想提取 access_token（依赖 jq）
AUTH_TOKEN=$(echo "$AUTH_TOKEN_RESP" | jq -r '.access_token')
#echo "$AUTH_TOKEN"
curl -s -w "\nHTTP_CODE: %{http_code}\n" -X POST http://localhost:8080/api/alerts/ingest \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $AUTH_TOKEN" \
  -d '{"source":"service-test","type":"pattern-test","resourceId":"resource-test","eventCode":"EVENT_TEST","severity":"CRITICAL","message":"manual test","occurredAt":"2026-11-26T12:00:00Z","payload":{}}'

# 然后查库确认
docker compose exec postgres psql -U triagedeck -d triagedeck -c "SELECT * FROM alerts_active WHERE source='service-test';"

-----------

AUTH_TOKEN=$(curl -s --location 'http://localhost:8081/realms/triagedeck/protocol/openid-connect/token' \
--header 'Content-Type: application/x-www-form-urlencoded' \
--data-urlencode 'grant_type=client_credentials' \
--data-urlencode 'client_id=ingest-service' \
--data-urlencode 'client_secret=Br772UsZFYYEntsV9Y0yz4lfPFAlP5Jw' | jq -r '.access_token')

python3 - <<END
import base64, json
TOKEN="$AUTH_TOKEN"
def b64dec(s):
    s += '=' * ((4 - len(s) % 4) % 4)
    return base64.b64decode(s).decode('utf-8')
h, p, _ = TOKEN.split('.')
print("==== HEADER ====")
print(json.dumps(json.loads(b64dec(h)), indent=2))
print("\n==== PAYLOAD ====")
print(json.dumps(json.loads(b64dec(p)), indent=2))
END

#AUTH_TOKEN=$(echo "$AUTH_TOKEN_RESP" | jq -r '.access_token')
#echo "$AUTH_TOKEN"
curl -s -w "\nHTTP_CODE: %{http_code}\n" -X POST http://localhost:8080/api/alerts/ingest \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $AUTH_TOKEN" \
  -d '{"source":"service-test","type":"pattern-test","resourceId":"resource-test","eventCode":"EVENT_TEST","severity":"CRITICAL","message":"manual test","occurredAt":"2026-11-26T12:00:00Z","payload":{}}'

# 然