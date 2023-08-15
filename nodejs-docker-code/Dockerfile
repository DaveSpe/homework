FROM node:16

ENV PGHOST my-db-instance.cgcoia1j17xe.us-east-1.rds.amazonaws.com
ENV PGUSER pgsqladmin
ENV PGPASSWORD sup3rS3cureP4ssw0rd
ENV PGDATABASE eavor-node-app
ENV PGPORT 5432

WORKDIR /app

COPY package*.json .

RUN npm install

COPY . .

CMD ["npm", "start"]
