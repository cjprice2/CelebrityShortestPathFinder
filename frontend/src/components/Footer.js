export default function Footer() {
  return (
    <footer className="bg-gray-900 text-gray-400 border-t border-gray-700">
      <div className="container mx-auto px-4 py-3">
        <div className="flex flex-col items-center gap-2">
          {/* TMDB Attribution */}
          <div className="flex items-center justify-center gap-2 text-sm">
            <span>Celebrity photos powered by</span>
            <img 
              src="https://www.themoviedb.org/assets/2/v4/logos/v2/blue_square_2-d537fb228cf3ded904ef09b136fe3fec72548ebc1fea3fbbd1ad9e36364db38b.svg" 
              alt="TMDB" 
              className="h-4 opacity-80"
            />
          </div>
          
          {/* Required Disclaimer */}
          <p className="text-xs text-gray-500 text-center">
            This application uses TMDB and the TMDB API for actor images but is not endorsed, 
            certified, or otherwise approved by TMDB. Celebrity dataset was filtered and cleaned from{" "}
            <a 
              href="https://developer.imdb.com/non-commercial-datasets/" 
              target="_blank" 
              rel="noopener noreferrer"
              className="text-blue-400 hover:text-blue-300 underline"
            >
              IMDb Non-Commercial Datasets
            </a>
            .
          </p>
          
          {/* App Info */}
          <div className="text-xs text-gray-600 pt-1 text-center">
            <p>Celebrity Shortest Path Finder - Find connections between celebrities through shared titles. Inspired by{" "}
              <a 
                href="https://en.wikipedia.org/wiki/Six_Degrees_of_Kevin_Bacon" 
                target="_blank" 
                rel="noopener noreferrer"
                className="text-blue-400 hover:text-blue-300 underline"
              >
                6 degrees of Kevin Bacon
              </a>
            </p>
          </div>
        </div>
      </div>
    </footer>
  );
}
