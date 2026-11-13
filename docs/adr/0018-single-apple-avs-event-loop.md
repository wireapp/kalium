# Single AVS event loop on Apple

AVS keeps its event loop, flow manager and media system in process globals. A process must start that loop once.

In an iOS app, `AVSFlowManager` owns the loop, as in the native iOS client. On first use, the bridge creates one `AVSFlowManager`, then the `AVSMediaManager` singleton, then calls `wcall_init`. The flow manager comes first, so the media system exists before the media manager registers its activation observer. Video previews, remote views and the speaker reuse these objects; Kalium never creates another `AVSFlowManager`.

App extensions and macOS keep `wcall_setup` and `wcall_run`, without UIKit media objects. On iOS, a main bundle ending in `.appex` selects this mode. The notification and share extensions do not start this runtime; the notification extension uses AVS's event API.

Startup runs once per process. Its success or failure is kept because AVS cannot be safely started again. Account handles stay separate: releasing, logging out or handing an account to an extension still destroys its `wcall` handle. The flow manager's loop keeps the media system alive between handles.

Previously, wcall ran its own loop and the first video view created an `AVSFlowManager`. That second loop returned at once and released the process media-system reference. Releasing the last account handle then freed the media system while the media manager's activation observer stayed registered, so the next app activation crashed in `msystem_activate`.
