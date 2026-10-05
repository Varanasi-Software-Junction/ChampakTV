# Play Store Release 1 — Learn With Champak Browser

Release: **3.0.0**  
Version code: **34**  
Package: **com.learnwithchampak.tv**  
Target SDK: **36**  
Minimum SDK: **21**

## Product position

Learn With Champak Browser is a lightweight browser designed for Android phones, tablets and Android TV. It combines normal browser controls with TV/D-pad navigation, session recovery, privacy blur, timed site opening and configurable tab rotation.

## Release 1 feature set

- Multiple tabs with previous-session reopen/discard
- Address bar with web search and visited-link suggestions
- Google, DuckDuckGo or Bing as the default search engine
- Bookmarks and visited history
- Downloads and open-last-downloaded-file
- Full-screen browsing
- Secure Google authentication via Android Custom Tabs
- Per-tab Privacy Blur
- Timed site opening (daily or repeating interval)
- Android TV / D-pad pointer navigation
- Rotate Tabs with configurable duration
- Quick Links with add, edit and remove controls
- Find on Page
- Share Page
- Copy Link
- Desktop/Mobile Site toggle
- Configurable web text size
- Clear browsing data controls
- Default-browser settings shortcut
- Privacy-policy shortcut

## Play Store compliance changes

- Direct APK self-update control removed from the Play UI.
- `REQUEST_INSTALL_PACKAGES` removed.
- Target/compile SDK raised to API 36.
- App label updated to **Learn With Champak Browser**.
- Release AAB build added.
- Release signing is supported through GitHub Actions secrets and never requires committing a private keystore.

## GitHub Actions signing secrets

Configure these repository secrets before the Play upload build:

- `PLAY_STORE_KEYSTORE_BASE64` — Base64 encoded upload keystore
- `PLAY_STORE_KEYSTORE_PASSWORD`
- `PLAY_STORE_KEY_ALIAS`
- `PLAY_STORE_KEY_PASSWORD`

If these are absent, the workflow still builds the debug APK and an unsigned release AAB for validation.

## Store listing draft

**App name**  
Learn With Champak Browser

**Short description**  
A lightweight browser with tabs, TV remote navigation, privacy tools and timed sites.

**Full description**  
Learn With Champak Browser is designed for phones, tablets and Android TV.

Browse with multiple tabs, reopen your previous session, save bookmarks, view history, download files, find text on a page and share links. Choose Google, DuckDuckGo or Bing for searches, switch between mobile and desktop sites, and adjust web text size.

For shared screens and TV use, Privacy Blur can hide selected tabs when the app loses focus. Rotate Tabs can automatically cycle through open tabs at a configurable interval. Timed Site Open can launch a chosen website daily or at a repeating interval.

Android TV users get D-pad navigation and an on-screen pointer, while Google authentication opens through Android secure Custom Tabs.

**Category suggestion**  
Tools

## Privacy and Data safety draft

Privacy policy:
https://programmer-s-picnic.github.io/json-images/tv/privacy-policy.html

Release 1 does not include a developer-operated account system, advertising SDK, analytics SDK or custom tracking SDK.

Browser state such as bookmarks, history, open tabs, preferences, quick links and timed-site settings is stored locally on the device. Websites visited by the user may independently collect data under their own policies. Google sign-in runs in a secure Custom Tab/external authentication surface.

Review the final Play Console Data safety questionnaire against the exact production binary before submission.

## Assets still needed for final Play Console submission

- 512×512 high-resolution app icon
- Feature graphic
- Phone screenshots
- Tablet screenshots
- Android TV screenshots / banner verification
- Final content-rating questionnaire
- Store contact/support details
- Closed-testing setup if required by the developer account

## Validation checklist

1. Install the debug APK on a phone.
2. Test startup, blank page, tab restore and discard.
3. Test Google sign-in.
4. Test bookmarks, history and quick links.
5. Test downloads and opening a downloaded file.
6. Test Rotate Tabs and changing the interval.
7. Test Find, Share and Copy Link.
8. Test Desktop/Mobile mode and text-size changes.
9. Test Privacy Blur by switching away from the app.
10. Test timed-site scheduling and reboot restoration.
11. Test default-browser intent handling.
12. Test D-pad navigation on Android TV/emulator.
13. Test the signed AAB through Play Internal Testing before closed/production rollout.
