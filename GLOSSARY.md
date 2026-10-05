# Calling

## Language

**Call signal**:
A message that exchanges call setup, participation or termination information between devices.
_Avoid_: Call presentation

**Call presentation**:
An incoming call offered to the user, with a stable identity and ringing or terminal state.
_Avoid_: Call signal

**Incoming call reference**:
The conversation, caller, optional caller client and message second observed in an incoming-call callback.
It can refer to several call starts and does not uniquely identify a real call.
_Avoid_: Call identity
