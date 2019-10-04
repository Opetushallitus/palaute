import 'semantic-ui-css/semantic.min.css';
import React, {useState} from "react";
import {withRouter} from "react-router";
import {Form, Input} from 'semantic-ui-react';


const App = (props) => {
    const query = new URLSearchParams(props.location.search).get("q");
    const [search, setSearch] = useState(
        query
    );

    const doSearch = () => {
        props.history.push(`?q=${search}`)
    };

    return (
        <Form onSubmit={doSearch}>
            <Input
                action={{
                    labelPosition: 'left',
                    icon: 'search',
                    content: 'Haku',
                }}
                fluid={true}
                actionPosition='left'
                placeholder='Haku...'
                onChange={event => setSearch(event.target.value)}
                defaultValue={search}
            />
        </Form>
    );
};

export default withRouter(App)
