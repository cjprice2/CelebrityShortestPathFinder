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

  const resolveNameToId = async (value, signal) => {
    const apiUrl = (process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080').replace(/\/$/, '');
    try {
      const res = await fetch(`${apiUrl}/api/search-celebrities-graph?q=${encodeURIComponent(value)}`, { signal });
      const data = await res.json();
      if (Array.isArray(data) && data.length > 0) {
        return data[0].nconst;
      }
    } catch (e) {
      // ignore
    }
    return "";
  };

  const isLikelyId = (v) => /^nm\d{1,9}$/.test(v);

  const handleSearch = async (input1, input2) => {
    // Begin loading immediately
    setLoading(true);
    setError("");
    setResults([]);
    setLoadingMsg("Finding shortest path");

    const apiUrl = (process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080').replace(/\/$/, '');

    // Resolve inputs to IDs concurrently if needed
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 8000); // cap name->ID at 8s

    const p1 = isLikelyId(input1) ? Promise.resolve(input1) : resolveNameToId(input1, controller.signal);
    const p2 = isLikelyId(input2) ? Promise.resolve(input2) : resolveNameToId(input2, controller.signal);

    let id1 = "";
    let id2 = "";

    try {
      [id1, id2] = await Promise.all([p1, p2]);
    } catch (e) {
      // if aborted or failed, fall through with empty ids
    } finally {
      clearTimeout(timeout);
    }

    if (!id1 || !id2) {
      setLoading(false);
      setError("One or both celebrities not found. Please refine names or pick from suggestions.");
      return;
    }

    const cacheKey = [id1, id2].sort().join('|');

    // Check cache
    if (cache.has(cacheKey)) {
      setResults(cache.get(cacheKey));
      setError("");
      setLoading(false);
      return;
    }

    try {
      const pathController = new AbortController();
      const pathTimeout = setTimeout(() => pathController.abort(), 60000); // fail after 60s
      const res = await fetch(`${apiUrl}/api/shortest-path?id1=${encodeURIComponent(id1)}&id2=${encodeURIComponent(id2)}&max=5`, { signal: pathController.signal });
      clearTimeout(pathTimeout);

      if (!res.ok) {
        if (res.status === 500) {
          throw new Error("No paths found, try a different pair of celebrities");
        } else if (res.status === 404) {
          throw new Error("One or both celebrities not found, try different names");
        } else {
          throw new Error("Unable to find connection, try a different pair of celebrities");
        }
      }

      const data = await res.json();
      if (data.error) {
        setError(data.error);
      } else {
        const arr = Array.isArray(data.results) ? data.results.slice(0,5) : [];
        setCache(prev => new Map(prev).set(cacheKey, arr));
        setResults(arr);
      }
    } catch (e) {
      if (e.name === 'AbortError') {
        setError("Search timed out. No connection found between these celebrities. Try a different pair of names.");
      } else {
        setError(e.message || "Unable to find connection, try a different pair of celebrities.");
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
      <main className="flex flex-col items-center justify-start p-4 sm:p-8 flex-grow">
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
