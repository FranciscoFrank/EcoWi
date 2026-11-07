# Getting help

EcoWi is an unofficial community project maintained in spare time. It has no connection to EcoFlow, so EcoFlow's support can't help with EcoWi, and EcoWi can't help with problems of the station itself.

## Before asking

1. Read the [README](README.md), especially [Getting started](README.md#getting-started) and [How it works](README.md#how-it-works).
2. Check the common problems below.
3. Search [existing issues](https://github.com/FranciscoFrank/EcoWi/issues?q=is%3Aissue). Someone may have had the same problem.

## Common problems

**Test fails with a sign-in error.**
Check the e-mail and password in the EcoFlow app first. If the account uses a non-default region, set the API server under *Connection → Advanced*.

**Test returns data, but charge or watts are empty.**
Your station or firmware may use different field names. Expand *Show raw keys* and open a *Station data report* issue with them (redact the serial number).

**Widgets update slowly.**
Without live mode, Android decides when background refreshes run, and intervals under 15 minutes are often delayed while the phone is idle. Turn on *Live mode* on the *Alerts* tab, and remove battery restrictions for EcoWi (*Settings → Apps → EcoWi → Battery → Unrestricted*).

**EcoWi doesn't appear in the Now Bar.**
Live Updates need Android 16 / One UI 8 and live mode. On some One UI builds you also need *Developer options → Live notifications for all apps*.

**The bar widget is 4×1 instead of 5×1.**
Switch One UI Home to a 5-column grid (*Home screen settings → Home screen grid → 5×5 / 5×6*).

**No alerts arrive.**
Allow notifications for EcoWi, check the per-alert switches on the *Alerts* tab, and make sure the notification channels aren't muted in system settings.

## Asking for help

Open an [issue](https://github.com/FranciscoFrank/EcoWi/issues/new/choose) and pick the template that fits. Useful details:

- phone model and Android / One UI version;
- station model and firmware version (EcoFlow app → station → ⚙ → About device);
- data source (EcoFlow account or Developer API) and whether live mode is on;
- what you expected and what happened instead;
- raw keys from *Connection → Test*, if the problem is about data;
- logs, if you can collect them: `adb logcat | grep -iE "ecowi|LiveService"`.

**Redact private data before posting.** Never share your password, tokens or MQTT credentials. Shorten serial numbers and user IDs (`R351ZE1A…XXXX`).

## Security issues

Don't report them in public issues. Follow [SECURITY.md](SECURITY.md).
