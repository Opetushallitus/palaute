import React from "react";
import ReactDOM from "react-dom";
import App from "./App.jsx"
import Results from "./Results.jsx"
import {Container} from 'semantic-ui-react'
import {BrowserRouter as Router} from "react-router-dom";

ReactDOM.render(
    <Router>
        <Container>
            <App/>
            <Results/>
        </Container>
    </Router>, document.getElementById("app"));
