#!/usr/bin/env bash
# Smoke test for a running backend.  Usage: ./api-tests.sh [base-url]
BASE=${1:-http://localhost:8080}/api/tasks
pass=0; fail=0
check() { # name expected actual
  if [ "$2" = "$3" ]; then echo "PASS  $1"; pass=$((pass+1)); else echo "FAIL  $1 (expected $2, got $3)"; fail=$((fail+1)); fi
}
code() { curl -s -o /dev/null -w '%{http_code}' "$@"; }
H='Content-Type: application/json'

R=$(curl -s -X POST $BASE -H "$H" -d '{"title":"Smoke test","description":"d","deadline":"2020-01-01","priority":"High"}')
ID=$(echo "$R" | sed -E 's/.*"id":([0-9]+).*/\1/')
check "POST create"                201 "$(code -X POST $BASE -H "$H" -d '{"title":"tmp"}')"
check "POST empty title -> 400"    400 "$(code -X POST $BASE -H "$H" -d '{"title":""}')"
check "GET list"                   200 "$(code $BASE)"
check "GET by id"                  200 "$(code $BASE/$ID)"
check "GET missing -> 404"         404 "$(code $BASE/999999)"
check "GET filter Pending"         200 "$(code "$BASE?status=Pending")"
check "GET search"                 200 "$(code "$BASE?search=smoke")"
check "GET overdue"                200 "$(code "$BASE?overdue=true")"
check "PUT update"                 200 "$(code -X PUT $BASE/$ID -H "$H" -d '{"title":"Smoke test 2","priority":"Low"}')"
check "PATCH status Completed"     200 "$(code -X PATCH $BASE/$ID/status -H "$H" -d '{"status":"Completed"}')"
check "PATCH bad status -> 400"    400 "$(code -X PATCH $BASE/$ID/status -H "$H" -d '{"status":"Nope"}')"
check "DELETE"                     204 "$(code -X DELETE $BASE/$ID)"
check "DELETE again -> 404"        404 "$(code -X DELETE $BASE/$ID)"
echo "Passed: $pass  Failed: $fail"; [ $fail -eq 0 ]
