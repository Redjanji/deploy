cd c:\Users\75328\OneDrive\桌面\xss\property-service
$env:NACOS_SERVER_ADDR=""
$env:MYSQL_HOST="127.0.0.1"
$env:MYSQL_USER="root"
$env:MYSQL_PASS="root"
$env:REDIS_HOST="127.0.0.1"
$env:REDIS_PASSWORD="redisroot"
$env:RABBITMQ_HOST="127.0.0.1"
$env:JWT_SECRET="eW91ci0yNTYtYml0LXNlY3JldC1rZXktZm9yLWp3dC1zaWduaW5nLWRldi1vbmx5"
$env:APP_SECRET_BACKEND="XssBackend2026SecHmacKey8xYz"
java -jar target\property-service-0.0.1-SNAPSHOT.jar
