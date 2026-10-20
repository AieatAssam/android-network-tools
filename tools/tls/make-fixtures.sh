#!/usr/bin/env bash
set -euo pipefail

# Generate public X.509 fixtures for the pure TLS certificate tests. Private keys
# and PKCS12 keystores are intentionally confined to this temporary directory.
repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
output_dir="$repo_root/core-network/src/test/resources/tls"
temporary_dir="$(mktemp -d "${TMPDIR:-/tmp}/netswissknife-tls-fixtures.XXXXXXXX")"
store_password="fixture-only-${RANDOM}-${RANDOM}"

cleanup() {
  rm -rf -- "$temporary_dir"
}
trap cleanup EXIT
umask 077
mkdir -p -- "$output_dir"

keytool -genkeypair \
  -alias test-root \
  -dname "CN=NetSwissKnife Test Root,O=NetSwissKnife Test Fixtures" \
  -keyalg RSA -keysize 2048 -validity 7300 \
  -startdate "2020/01/01 00:00:00" \
  -ext bc=ca:true -ext ku=keyCertSign,cRLSign \
  -keystore "$temporary_dir/root.p12" -storetype PKCS12 \
  -storepass "$store_password" -keypass "$store_password" \
  -noprompt >/dev/null 2>&1
keytool -exportcert -rfc -alias test-root \
  -keystore "$temporary_dir/root.p12" -storepass "$store_password" \
  -file "$output_dir/root.pem" >/dev/null 2>&1

make_signed_leaf() {
  local alias="$1"
  local filename="$2"
  local key_size="$3"
  local subject="$4"
  local san="$5"
  local start_date="$6"
  local validity_days="$7"
  local store="$temporary_dir/$alias.p12"

  keytool -genkeypair \
    -alias "$alias" -dname "$subject" \
    -keyalg RSA -keysize "$key_size" -validity "$validity_days" \
    -startdate "$start_date" \
    -keystore "$store" -storetype PKCS12 \
    -storepass "$store_password" -keypass "$store_password" \
    -noprompt >/dev/null 2>&1
  keytool -certreq -rfc -alias "$alias" \
    -keystore "$store" -storepass "$store_password" \
    -file "$temporary_dir/$alias.csr" >/dev/null 2>&1
  keytool -gencert -rfc -alias test-root \
    -keystore "$temporary_dir/root.p12" -storepass "$store_password" \
    -infile "$temporary_dir/$alias.csr" -outfile "$temporary_dir/$alias.pem" \
    -validity "$validity_days" -startdate "$start_date" \
    -ext bc=ca:false -ext ku=digitalSignature,keyEncipherment \
    -ext eku=serverAuth -ext "$san" >/dev/null 2>&1
  keytool -importcert -noprompt -alias test-root \
    -file "$output_dir/root.pem" \
    -keystore "$store" -storepass "$store_password" >/dev/null 2>&1
  keytool -importcert -noprompt -alias "$alias" \
    -file "$temporary_dir/$alias.pem" \
    -keystore "$store" -storepass "$store_password" >/dev/null 2>&1
  cp -- "$temporary_dir/$alias.pem" "$output_dir/$filename"
}

make_signed_leaf valid-leaf valid-leaf.pem 2048 \
  "CN=cn-fallback.example.com,O=NetSwissKnife Test Fixtures" \
  "san=dns:www.example.com" "2026/01/01 00:00:00" 3650
make_signed_leaf wildcard-leaf wildcard-leaf.pem 2048 \
  "CN=*.example.com,O=NetSwissKnife Test Fixtures" \
  "san=dns:*.example.com" "2026/01/01 00:00:00" 3650
make_signed_leaf ip-san-leaf ip-san-leaf.pem 2048 \
  "CN=ip-san.test.invalid,O=NetSwissKnife Test Fixtures" \
  "san=ip:1.1.1.1" "2026/01/01 00:00:00" 3650
make_signed_leaf ipv6-san-leaf ipv6-san-leaf.pem 2048 \
  "CN=ipv6-san.test.invalid,O=NetSwissKnife Test Fixtures" \
  "san=ip:2001:db8::1" "2026/01/01 00:00:00" 3650
make_signed_leaf expired-leaf expired-leaf.pem 2048 \
  "CN=expired.example.com,O=NetSwissKnife Test Fixtures" \
  "san=dns:expired.example.com" "2020/01/01 00:00:00" 2
make_signed_leaf not-yet-valid-leaf not-yet-valid-leaf.pem 2048 \
  "CN=future.example.com,O=NetSwissKnife Test Fixtures" \
  "san=dns:future.example.com" "2030/01/01 00:00:00" 3650
make_signed_leaf weak-rsa-1024-leaf weak-rsa-1024-leaf.pem 1024 \
  "CN=weak.example.com,O=NetSwissKnife Test Fixtures" \
  "san=dns:weak.example.com" "2026/01/01 00:00:00" 3650

# The self-signed fixture is intentionally not imported into the test root.
keytool -genkeypair \
  -alias self-signed \
  -dname "CN=self-signed.example.com,O=NetSwissKnife Test Fixtures" \
  -keyalg RSA -keysize 2048 -validity 3650 \
  -startdate "2026/01/01 00:00:00" \
  -ext bc=ca:false -ext ku=digitalSignature,keyEncipherment \
  -ext eku=serverAuth \
  -keystore "$temporary_dir/self-signed.p12" -storetype PKCS12 \
  -storepass "$store_password" -keypass "$store_password" \
  -noprompt >/dev/null 2>&1
keytool -exportcert -rfc -alias self-signed \
  -keystore "$temporary_dir/self-signed.p12" -storepass "$store_password" \
  -file "$output_dir/self-signed.pem" >/dev/null 2>&1

printf 'Generated PEM certificate fixtures in %s\n' "$output_dir"
for fixture in "$output_dir"/*.pem; do
  normalized="$fixture.lf"
  tr -d '\015' < "$fixture" > "$normalized"
  mv -- "$normalized" "$fixture"
  printf '%s\n' "${fixture##*/}"
done | sort
