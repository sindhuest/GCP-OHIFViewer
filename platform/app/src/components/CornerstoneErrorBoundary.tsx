import React from 'react';

class CornerstoneErrorBoundary extends React.Component {
    constructor(props) {
        super(props);
        this.state = { hasError: false, error: null };
    }

    static getDerivedStateFromError(error) {
        return { hasError: true, error };
    }

    componentDidCatch(error, errorInfo) {
        console.error('Cornerstone Error:', error, errorInfo);
    }

    render() {
        if (this.state.hasError) {
            return (
                <div className="error-container">
                    <h2>Something went wrong loading the image.</h2>
                    <p>Please try refreshing the page or contact support if the problem persists.</p>
                </div>
            );
        }

        return this.props.children;
    }
}
