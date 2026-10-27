import React from 'react';
import { createRoot } from 'react-dom/client';
import App from './App.jsx';
import './woori-theme.css';
import './conversation-flow.css';
import './simulation-flow.css';
import './reference-flow.css';

createRoot(document.getElementById('root')).render(<App/>);
