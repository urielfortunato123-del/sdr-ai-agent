import { useState, useEffect } from 'react'
import './App.css'

interface Lead {
  id: number;
  name: string;
  city: string;
  category: string;
  rating?: number;
  reviews_count?: number;
  analysis?: Analysis;
}

interface Analysis {
  score: number;
  potential_level: string;
  marketing_diagnosis: string;
  generated_message: string;
}

function App() {
  const [query, setQuery] = useState('')
  const [leads, setLeads] = useState<Lead[]>([])
  const [loading, setLoading] = useState(false)

  const fetchLeads = async () => {
    const res = await fetch('http://localhost:8000/leads')
    const data = await res.json()
    setLeads(data)
  }

  useEffect(() => {
    fetchLeads()
  }, [])

  const handleSearch = async () => {
    setLoading(true)
    try {
      await fetch(`http://localhost:8000/leads/search?query=${query}`, { method: 'POST' })
      await fetchLeads()
    } finally {
      setLoading(false)
    }
  }

  const handleAnalyze = async (id: number) => {
    const res = await fetch(`http://localhost:8000/leads/${id}/analyze`, { method: 'POST' })
    const analysis = await res.json()
    setLeads(leads.map(l => l.id === id ? { ...l, analysis } : l))
  }

  return (
    <div className="dashboard">
      <header>
        <h1>Antigravidade SDR 🚀</h1>
        <div className="stats">
          Total Leads: {leads.length}
        </div>
      </header>

      <div className="search-bar">
        <input 
          placeholder="Ex: Advogados em Bauru..." 
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
        <button onClick={handleSearch} disabled={loading}>
          {loading ? 'Buscando...' : 'Coletar Leads'}
        </button>
      </div>

      <div className="lead-grid">
        {leads.map(lead => (
          <div key={lead.id} className="lead-card">
            <div className="lead-header">
              <div className="lead-title">
                <h3>{lead.name}</h3>
                <span className="lead-category">{lead.category}</span>
              </div>
              {lead.analysis && (
                <div className={`score-badge score-${lead.analysis.potential_level}`}>
                  {lead.analysis.score}/100
                </div>
              )}
            </div>

            <div className="lead-info">
              <p>📍 {lead.city}</p>
              <p>⭐ {lead.rating} ({lead.reviews_count} avaliações)</p>
            </div>

            {lead.analysis ? (
              <>
                <div className="diagnosis">
                  <strong>Diagnóstico:</strong>
                  <p>{lead.analysis.marketing_diagnosis}</p>
                </div>
                <div className="message-box">
                  {lead.analysis.generated_message}
                </div>
                <div className="actions">
                  <button className="btn-whatsapp" onClick={() => window.open(`https://wa.me/?text=${encodeURIComponent(lead.analysis!.generated_message)}`)}>
                    Enviar p/ WhatsApp
                  </button>
                </div>
              </>
            ) : (
              <button onClick={() => handleAnalyze(lead.id)}>
                Analisar com Antigravidade
              </button>
            )}
          </div>
        ))}
      </div>
    </div>
  )
}

export default App
