# Still product commitments

Still is a local-first Android screen-time app. Community themes extend its existing appearance without replacing built-in themes or weakening access to settings.

- Theme Compose remains version 1 and uses portable common fields with registered product extensions.
- Theme source and bounded image bundles are parsed and evaluated locally. Themes cannot execute code or read arbitrary files, usage history, credentials, or installed app lists.
- Every data request starts denied. Installation shows the host capability description separately from the author's reason. Users can later revoke consent or remove a theme.
- Files use the Android document picker. Links require public HTTPS; source hosts see the download request and IP address, while usage data and request values stay local.
- Daily link checks are optional and start off. Updates stay pending until reviewed and installed. New or revised requests require consent again.
- Permission, removal and recovery controls use trusted host styling. Downloads and update checks can be cancelled; short atomic storage commits finish before dismissal.
- The existing Still components, Material typography, native bottom drawers and accessibility behavior remain the visual authority.
