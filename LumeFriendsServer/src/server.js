'use strict';

require('dotenv').config();
const path = require('path');
const express = require('express');
const cors = require('cors');
const friendsRouter = require('./routes/friends');
const versionRouter = require('./routes/version');

const app = express();
app.use(cors());
app.use(express.json());
app.use(express.static(path.join(__dirname, '..', 'public')));

app.get('/health', (req, res) => res.json({ ok: true }));

app.use('/api', friendsRouter);
app.use('/api', versionRouter);

const port = process.env.PORT || 8079;
app.listen(port, () => console.log(`[LumeFriendsServer] listening on :${port}`));
