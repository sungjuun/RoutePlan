FROM node:22-alpine

WORKDIR /stub
COPY google-routes-stub.mjs /stub/server.mjs

USER node
EXPOSE 8090
CMD ["node", "/stub/server.mjs"]
