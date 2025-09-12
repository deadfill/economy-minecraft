#!/bin/bash

if ! [ -d libraries ]; then
    ln -s /app/libraries libraries
fi

if ! [ -d versions ]; then
    ln -s /app/versions versions
fi

if ! [ -d .fabric ]; then
    mkdir .fabric
    ln -s /app/.fabric/server .fabric/server
fi

if ! [ -f ServerWrapperConfig.json ]; then
    ln -s /app/ServerWrapperConfig.json ServerWrapperConfig.json
fi

if ! [ -f javaargs.txt ]; then
    echo "" > javaargs.txt
fi

exec java @javaargs.txt -cp /app/ServerWrapper.jar pro.gravit.launcher.server.ServerWrapper
