import { Pool } from 'pg';

const dbHost = process.env.PGHOST;
const dbUser = process.env.PGUSER;
const dbPassword = process.env.PGPASSWORD;
const dbName = process.env.PGDATABASE;
const dbPort = process.env.PGPORT;

const connectionString = `postgresql://${dbUser}:${dbPassword}@${dbHost}:${dbPort}/${dbName}`;

const pool = new Pool({
  connectionString,
});

console.log('Trying to connect to db');

try {
  await pool.connect();
  console.log('Connected successfully to server');
} catch (error) {
  console.log('Connection failed.');
  await pool.end();
  console.log('Connection closed.');
  process.exit(1);
}

const database = pool;

export default database;
