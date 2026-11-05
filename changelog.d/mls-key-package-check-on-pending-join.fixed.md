After joining pending MLS conversations by external commit, clients now check their key package count on the backend right away if there was a group to join. Being left out of a group can mean that other clients claimed all of this client's key packages, and until the next scheduled check nobody could add it to new groups.

  - ABI: unchanged.
  - Source: unchanged.
