Clients now check their key package count on the backend at the first live sync an hour after the last check, instead of a day after. Other clients claim key packages without this client's local count changing, so a client could stay at zero key packages for up to a day, and nobody could add it to new groups in that time. The refill itself is unchanged: 100 new key packages when fewer than 50 are left.

  - ABI: unchanged.
  - Source: unchanged.
