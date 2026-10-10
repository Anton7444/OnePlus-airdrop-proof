# OnePlus-airdrop-proof

Minimal Wi-Fi Aware (NAN) probe for a OnePlus 13T (China ColorOS), used to find out whether the
Qualcomm Wi-Fi stack can really run Wi-Fi Aware once OPLUS's feature flag
(`android.hardware.wifi.aware`) is re-enabled.

## Build

GitHub Actions builds a debug APK on every push. Download it from
**Actions -> latest run -> Artifacts -> aware-test-debug-apk**.

## Use

1. Make sure the `android.hardware.wifi.aware` feature is visible
   (`pm list features | grep -i aware`).
2. Install the APK, open it, grant the permissions.
3. Tap **Check**, then **Attach**, then **Publish test service**.
4. Copy the on-screen log.

What the result means:

| Result | Meaning |
| --- | --- |
| `onAttached: OK` and characteristics printed | HAL + firmware support Wi-Fi Aware |
| `onAttachFailed`, characteristics stay `null` | Aware cannot run on this device |
| `WifiAwareManager: null` | The feature flag is not active (re-apply the mount) |
