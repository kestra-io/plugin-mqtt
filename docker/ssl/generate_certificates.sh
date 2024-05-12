#!/bin/bash

IP="localhost"
SUBJECT_CA="/C=SE/ST=Stockholm/L=Stockholm/O=himinds/OU=CA/CN=$IP"
SUBJECT_SERVER="/C=SE/ST=Stockholm/L=Stockholm/O=himinds/OU=Server/CN=$IP"
SUBJECT_CLIENT="/C=SE/ST=Stockholm/L=Stockholm/O=himinds/OU=Client/CN=$IP"

CERTS_DIR="certs/"

function generate_CA () {
   echo "$SUBJECT_CA"
   openssl req -x509 -nodes -sha256 -newkey rsa:2048 -subj "$SUBJECT_CA"  -days 365 -keyout "${CERTS_DIR}ca.key" -out "${CERTS_DIR}ca.crt"
}

function generate_server () {
   echo "$SUBJECT_SERVER"
   openssl req -nodes -sha256 -new -subj "$SUBJECT_SERVER" -keyout "${CERTS_DIR}server.key" -out "${CERTS_DIR}server.csr"
   openssl x509 -req -sha256 -in "${CERTS_DIR}server.csr" -CA "${CERTS_DIR}ca.crt" -CAkey "${CERTS_DIR}ca.key" -CAcreateserial -out "${CERTS_DIR}server.crt" -days 365
   sudo chmod a+r "${CERTS_DIR}server.key"  # Add this line to change permissions
}

function generate_client () {
   echo "$SUBJECT_CLIENT"
   openssl req -new -nodes -sha256 -subj "$SUBJECT_CLIENT" -out "${CERTS_DIR}client.csr" -keyout "${CERTS_DIR}client.key" 
   openssl x509 -req -sha256 -in "${CERTS_DIR}client.csr" -CA "${CERTS_DIR}ca.crt" -CAkey "${CERTS_DIR}ca.key" -CAcreateserial -out "${CERTS_DIR}client.crt" -days 365
}

generate_CA
generate_server
generate_client

