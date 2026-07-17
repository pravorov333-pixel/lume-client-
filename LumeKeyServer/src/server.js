'use strict';

require('dotenv').config();
const path = require('path');
const express = require('express');
const cors = require('cors');
const keysRouter = require('./routes/keys');
const adminRouter = require('./routes/admin');

const app = express();
app.use(cors());
app.use(express.json());
app.use(express.static(path.join(__dirname, '..', 'public')));

app.get('/health', (req, res) => res.json({ ok: true }));

app.use('/api/keys', keysRouter);
app.use('/api/admin', adminRouter);

const port = process.env.PORT || 3000;
app.listen(port, () => console.log(`[LumeKeyServer] listening on :${port}`));
