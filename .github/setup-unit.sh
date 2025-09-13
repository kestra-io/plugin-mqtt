mkdir -p certs src/test/resources/crt src/test/resources/mosquitto

if [ ! -f certs/ca.crt ]; then
  openssl genrsa -out certs/ca.key 2048
  openssl req -x509 -new -nodes -key certs/ca.key -sha256 -days 365 \
    -subj "/CN=MQTT Test CA" -out certs/ca.crt

  openssl genrsa -out certs/server.key 2048
  openssl req -new -key certs/server.key -out certs/server.csr \
    -subj "/CN=localhost"
  openssl x509 -req -in certs/server.csr -CA certs/ca.crt -CAkey certs/ca.key \
    -CAcreateserial -out certs/server.crt -days 365 -sha256 \
    -extfile <(printf "subjectAltName=DNS:localhost,IP:127.0.0.1")
fi

chmod 644 certs/ca.crt certs/server.crt certs/server.key
cp certs/ca.crt src/test/resources/crt/ca.crt

cat > src/test/resources/mosquitto/mosquitto.conf <<'EOF'
# Plain MQTT
listener 1883 0.0.0.0
protocol mqtt
allow_anonymous true

# TLS MQTT
listener 8883 0.0.0.0
protocol mqtt
cafile /crt/ca.crt
certfile /crt/server.crt
keyfile /crt/server.key
allow_anonymous true
EOF

docker compose -f docker-compose-ci.yml up -d
until nc -z 127.0.0.1 1883; do sleep 1; done
echo "MQTT broker started on 1883 (plain) and 8883 (TLS)"
