$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath (Split-Path -Parent $PSScriptRoot)
if (-not $env:DB_PASSWORD) { throw 'Set DB_PASSWORD for your local MySQL root account first.' }
$env:DB_URL='jdbc:mysql://127.0.0.1:3306/student_hub_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC'
$env:UPLOAD_DIR=Join-Path $PWD 'runtime\test-uploads'
$env:COLLABHUB_INTEGRATION='true'
try {
    mvn "-Dmaven.repo.local=$PWD\.maven-repo" test-compile dependency:build-classpath '-Dmdep.outputFile=target/classpath.txt' '-Dmdep.includeScope=test' -DskipTests -q
    if ($LASTEXITCODE -ne 0) { throw 'Compilation failed.' }
    $collabClasspath='target\test-classes;target\classes;'+(Get-Content target\classpath.txt -Raw).Trim()
    java -cp $collabClasspath com.collabhub.InitializeTestDb
    if ($LASTEXITCODE -ne 0) { throw 'Test database initialization failed.' }
    mvn "-Dmaven.repo.local=$PWD\.maven-repo" test
    if ($LASTEXITCODE -ne 0) { throw 'Integration tests failed. See target/surefire-reports.' }
} finally {
    Remove-Item Env:DB_URL,Env:UPLOAD_DIR,Env:COLLABHUB_INTEGRATION -ErrorAction SilentlyContinue
}
