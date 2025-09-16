"use client";
import { useEffect, useState } from "react";
import Header from "../components/Header";
import SearchForm from "../components/SearchForm";
import PathResult from "../components/PathResult";
import Footer from "../components/Footer";

export default function HomePage() {
  const [results, setResults] = useState([]);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [cache, setCache] = useState(new Map());
  const [loadingMsg, setLoadingMsg] = useState("Finding shortest path");

  const handleSearch = async (actor1, actor2) => {
    const cacheKey = [actor1, actor2].sort().join('|');
    
    // Check cache first
    if (cache.has(cacheKey)) {
      setResults(cache.get(cacheKey));
      setError("");
      return;
    }
    
    setLoading(true);
    setError("");
    setResults([]);
    setLoadingMsg("Finding shortest path");
    try {
      const controller = new AbortController();
      const timeout = setTimeout(() => controller.abort(), 60000); // fail after 60s
      const res = await fetch(`/api/shortest-path?id1=${encodeURIComponent(actor1)}&id2=${encodeURIComponent(actor2)}&max=5`, { signal: controller.signal });
      clearTimeout(timeout);

      if (!res.ok) {
        // Immediately surface server error
        throw new Error(`Backend error: ${res.status} ${res.statusText}`);
      }

      const data = await res.json();
      if (data.error) {
        setError(data.error);
      } else {
        const arr = Array.isArray(data.results) ? data.results.slice(0,5) : [];
        // Cache the successful results array
        setCache(prev => new Map(prev).set(cacheKey, arr));
        setResults(arr);
      }
    } catch (e) {
      if (e.name === 'AbortError') {
        setError("Backend took too long to respond. There might be no connection between these actors. Try a different pair of names.");
      } else {
        setError(e.message || "Failed to connect to backend API.");
      }
    } finally {
      setLoading(false);
    }
  };

  // After 5s of continuous loading, soften the message
  useEffect(() => {
    if (!loading) return;
    const t = setTimeout(() => {
      setLoadingMsg("Still loading, stay patient");
    }, 5000);
    return () => clearTimeout(t);
  }, [loading]);

  return (
    <div className="min-h-screen bg-gradient-to-b from-gray-900 to-gray-800 flex flex-col">
      <Header />
      <main className="flex flex-col items-center justify-start p-8 flex-grow">
        <SearchForm onSearch={handleSearch} />
        {loading && (
          <div className="flex items-center gap-3 mt-6 text-blue-400">
            <div className="w-6 h-6 border-2 border-blue-400 border-t-transparent rounded-full animate-spin"></div>
            <span>
              {loadingMsg}
              <span className="inline-block">
                <span className="animate-pulse">.</span>
                <span className="animate-pulse" style={{animationDelay: '0.2s'}}>.</span>
                <span className="animate-pulse" style={{animationDelay: '0.4s'}}>.</span>
              </span>
            </span>
          </div>
        )}
        {error && <div className="text-red-400 mt-6 max-w-xl text-center">{error}</div>}
        {results.length > 0 && !error && (
          <div className="mt-20 mb-16 flex flex-col gap-12">
            {results.map((res, idx) => (
              <div key={idx}>
                <div className="text-white font-bold text-lg mb-2 text-center sm:text-left">Shortest Path #{idx + 1}</div>
                <PathResult result={res} />
              </div>
            ))}
          </div>
        )}
      </main>
      <Footer />
    </div>
  );
}
