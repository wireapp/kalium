Added opt-in API 18 negotiation and network containers, including `ConversationApi.replaceMembers` for replacing direct conversation members. API 18 preserves eligible members in adminless-conversation errors and inherits existing meeting and notification behavior.

  - ABI: the network API gains a default interface method. Rebuild consumer implementations and Apple frameworks before using it.
  - Source: additive. Existing conversation API implementations inherit an unsupported result below API 18.
  - Behavior: API 18 is selected only when development APIs are enabled; stable API negotiation is unchanged.
  - Migration: no action required unless callers opt into API 18 and use member replacement.
