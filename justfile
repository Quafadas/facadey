
checkJvmVersion:
  @if [ "$(java -version 2>&1 | awk -F[\".] 'NR==1{print $2}')" -lt 18 ]; then
    echo "JVM version is less than 18. Please update your JVM."
    exit 1
  else
    echo "JVM version is 18 or greater."
  fi

setupPlaywright:
  cs launch com.microsoft.playwright:playwright:1.45.0 -M "com.microsoft.playwright.CLI" -- install --with-deps

tsc:
  npm install typescript --save-dev