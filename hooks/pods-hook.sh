#!/usr/bin/env bash

if [[ $1 == "--config" ]] ; then
  cat <<EOF
configVersion: v1
kubernetes:
- apiVersion: v1
  kind: Pod
  executeHookOnEvent: ["Added"]
  namespace:
    nameSelector:
      matchNames: ["teamdagpenger"]
EOF
else
  podName=$(jq -r .[0].object.metadata.name $BINDING_CONTEXT_PATH)
  echo "Pod '${podName}' added"
fi
