A client that is added to an MLS group but gets no Welcome, for example because it had no key packages left, now joins the group by external commit. Before, it waited for the next slow sync or for a key package check that found none, which runs about once a day, and in the meantime it received none of the group's messages. Both the app and the notification processor flag the join; the app joins once incremental sync has stayed live for 10 seconds, so a Welcome that is still on its way is processed first.

  - ABI: unchanged.
  - Source: unchanged.
