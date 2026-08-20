#!/usr/bin/env bash
set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
zombie_buddy_jar=${ZOMBIE_BUDDY_JAR:-/home/cjstorrs/games/Project Zomboid Linux 42.20.0/game/projectzomboid/ZombieBuddy.jar}
project_zomboid_jar=${PROJECT_ZOMBOID_JAR:-/home/cjstorrs/games/Project Zomboid Linux 42.20.0/game/projectzomboid/projectzomboid.jar}
project_zomboid_java=${PROJECT_ZOMBOID_JAVA:-$(dirname -- "$project_zomboid_jar")/jre64/bin/java}
build_dir="$root_dir/build"
stub_classes="$build_dir/classes/stub"
main_classes="$build_dir/classes/main"
test_classes="$build_dir/classes/test"
linkage_test_classes="$build_dir/classes/linkage-test"
output_jar="$root_dir/42/media/java/CJSFirearmAimingOverhaul.jar"

if [ ! -f "$zombie_buddy_jar" ]; then
    echo "ZombieBuddy JAR not found: $zombie_buddy_jar" >&2
    exit 1
fi

if [ ! -f "$project_zomboid_jar" ]; then
    echo "Project Zomboid JAR not found: $project_zomboid_jar" >&2
    exit 1
fi

if [ ! -x "$project_zomboid_java" ]; then
    echo "Project Zomboid Java runtime not found: $project_zomboid_java" >&2
    exit 1
fi

compile_classpath="$stub_classes:$zombie_buddy_jar"

mkdir -p "$stub_classes" "$main_classes" "$test_classes" "$linkage_test_classes" "$(dirname -- "$output_jar")"
find "$stub_classes" "$main_classes" "$test_classes" "$linkage_test_classes" -type f -name '*.class' -delete

mapfile -d '' stub_sources < <(find "$root_dir/src/stub/java" -type f -name '*.java' -print0 | sort -z)
if [ "${#stub_sources[@]}" -eq 0 ]; then
    echo "No compile-only Project Zomboid API stubs found" >&2
    exit 1
fi

javac --release 17 -d "$stub_classes" "${stub_sources[@]}"

mapfile -d '' main_sources < <(find "$root_dir/src/main/java" -type f -name '*.java' -print0 | sort -z)
if [ "${#main_sources[@]}" -eq 0 ]; then
    echo "No main Java sources found" >&2
    exit 1
fi

javac --release 17 -cp "$compile_classpath" -d "$main_classes" "${main_sources[@]}"
jar --create --file "$output_jar" --date=2000-01-01T00:00:00Z -C "$main_classes" .

mapfile -d '' test_sources < <(find "$root_dir/src/test/java" -type f -name '*.java' -print0 | sort -z)
if [ "${#test_sources[@]}" -gt 0 ]; then
    javac --release 17 -cp "$main_classes:$compile_classpath" -d "$test_classes" "${test_sources[@]}"
    java -ea -cp "$test_classes:$main_classes:$compile_classpath" com.cjstorrs.firearmaimingoverhaul.FirearmAimingPatchTest
fi

mapfile -d '' linkage_test_sources < <(find "$root_dir/src/linkageTest/java" -type f -name '*.java' -print0 | sort -z)
if [ "${#linkage_test_sources[@]}" -eq 0 ]; then
    echo "No Project Zomboid runtime linkage tests found" >&2
    exit 1
fi

javac --release 17 -d "$linkage_test_classes" "${linkage_test_sources[@]}"
"$project_zomboid_java" -ea \
    -cp "$linkage_test_classes:$main_classes:$zombie_buddy_jar:$project_zomboid_jar" \
    com.cjstorrs.firearmaimingoverhaul.GameApiLinkageTest

echo "Built: $output_jar"
