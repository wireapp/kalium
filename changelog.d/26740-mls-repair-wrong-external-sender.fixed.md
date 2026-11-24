The MLS removal-key repair now also resets groups of the self domain whose external sender is not the backend's current removal key, runs without configured faulty keys, and reruns when the backend's removal keys change. Groups not established on the client are skipped instead of failing the repair.

  - ABI: unchanged
  - Source: unchanged
  - Behavior: the repair runs once per configuration for every user
