Calls answered on another client no longer create missed-call messages when they close.
Group calls remain ongoing on the other clients, using AVS 10.5.33 on Android and Apple.

  - ABI: breaking for `CallMetadata` constructor, copy, and component methods because `callAnsweredElsewhere` is added before `participants`; recompile consumers.
  - Source: `callAnsweredElsewhere` defaults to false. Positional constructor or copy arguments after `callStatus`, and destructuring after that field, must account for the new Boolean.
  - Behavior: JVM, Android, and Apple call handlers remember when a call was answered elsewhere and suppress its later missed-call message.
  - Migration: recompile consumers and use named arguments for `CallMetadata` construction and copying.
