import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { HomePage } from './pages/HomePage';
import { Dashboard } from './pages/Dashboard';
import './App.css';

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="/dashboard" element={<Dashboard />} />
        <Route path="*" element={
          <div className="page">
            <div className="card">
              <h1>404: Not Found</h1>
              <a href="/" className="button">Go Home</a>
            </div>
          </div>
        } />
      </Routes>
    </BrowserRouter>
  );
}

export default App;
