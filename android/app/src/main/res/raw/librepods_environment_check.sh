# Read-only diagnostic; no mounts, package changes, service stops or permission grants.
PATH=/system/bin:/system/xbin:/vendor/bin
export PATH
printf 'LibrePods mount check v1\n'
if [ "$(id -u)" != 0 ]; then
    printf 'ROOT=denied\n'
    exit 1
fi
printf 'ROOT=granted\n'
show_file() {
    if [ -f "$2" ]; then printf '%s=visible\n' "$1"; else printf '%s=not_visible\n' "$1"; fi
}
show_file MODULE_INSTALLED /data/adb/modules/librepods/module.prop
show_file MODULE_PENDING_UPDATE /data/adb/modules_update/librepods/module.prop
for marker in disable remove skip_mount; do
    show_file "MODULE_FLAG_$marker" "/data/adb/modules/librepods/$marker"
done
if [ -e /data/adb/metamodule/module.prop ]; then
    printf 'METAMODULE:\n'
    sed -n '/^id=/p; /^name=/p; /^version=/p' /data/adb/metamodule/module.prop
else
    printf 'METAMODULE=not_visible_at_standard_path\n'
fi
show_file ROOT_VIEW_APK /system/priv-app/LibrePods/LibrePods.apk
show_file ROOT_VIEW_PERMISSIONS /system/etc/permissions/privapp-permissions-librepods.xml
server_pid=$(pidof system_server)
server_pid=${server_pid%% *}
if [ -n "$server_pid" ]; then
    show_file SYSTEM_SERVER_VIEW_APK "/proc/$server_pid/root/system/priv-app/LibrePods/LibrePods.apk"
    show_file SYSTEM_SERVER_VIEW_PERMISSIONS "/proc/$server_pid/root/system/etc/permissions/privapp-permissions-librepods.xml"
else
    printf 'SYSTEM_SERVER_VIEW=unavailable\n'
fi
printf 'PACKAGE_MANAGER:\n'
pm path me.kavishdevar.librepods 2>&1
# Output only package identity, system flags and the two relevant permission results.
dumpsys package me.kavishdevar.librepods 2>/dev/null | grep -E 'codePath=|resourcePath=|versionName=|pkgFlags=|flags=|privateFlags=|BLUETOOTH_PRIVILEGED:|MODIFY_PHONE_STATE:' | head -20
printf 'VENDOR_DEVICE_PACKAGES:\n'
pm list packages -s 2>/dev/null | grep -Ei 'mydevices|airpods|heytap.*headset|oplus.*earphone|oplus.*bluetooth' | head -12
printf 'END\n'
