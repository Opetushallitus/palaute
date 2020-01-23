import 'semantic-ui-css/semantic.min.css';
import React, {useState, useEffect} from "react";
import {withRouter} from "react-router";
import {Table, Form, Label, Button, Icon, Rating} from 'semantic-ui-react';
import Excel from './Excel';
import * as moment from 'moment';
import 'moment/locale/fi';
import parser from 'ua-parser-js';

const DataRows = ({data}) => {
    return <React.Fragment>{(data || []).map((row, index) => {
        const ua = row[2] && parser(row[2]) || {browser: {}, os: {}};
        return <Table.Row key={index}>
            <Table.Cell collapsing><Rating defaultRating={row[1]} maxRating={5} disabled/></Table.Cell>
            <Table.Cell singleline>{row[3]}</Table.Cell>
            <Table.Cell collapsing>{moment(row[0]).format('LLL')}</Table.Cell>
            <Table.Cell>
                {`${ua.browser.name} ${ua.browser.major}, ${ua.os.name} ${ua.os.version}`}
                {ua.device && ua.device.type && `, ${ua.device.type} ${ua.device.model}`}
            </Table.Cell>
        </Table.Row>;
    })}</React.Fragment>;
};

const ShowMore = ({moreFn}) => {
    return <Table.Footer fullWidth>
        <Table.Row>
            <Table.HeaderCell colSpan='4' className={"stackable center aligned page grid"}>
                <Button
                    icon
                    labelPosition='left'
                    primary
                    onClick={moreFn}>
                    <Icon name='angle double down'/>Näytä lisää
                </Button>
            </Table.HeaderCell>
        </Table.Row>
    </Table.Footer>;
};

const Loader = () => {
    return <Table.Row>
        <Table.Cell collapsing colspan="4">
            <div className="ui active centered inline loader"/>
        </Table.Cell>
    </Table.Row>;
};

const Results = (props) => {
    const query = new URLSearchParams(props.location.search).get("q");
    const [state , setState] = useState({loading: false});
    useEffect(() => {

        const runEffect = async () => {
            setState({...state, loading: true});
            const data = await fetch(
                "/palaute/api/palaute?q=" + query, {
                    credentials: "same-origin"
                }
            ).then(r => {
                if(r.status === 200) {
                    return r.json();
                } else if (r.status === 401) {
                    console.log('Ei käyttöoikeuksia! ' + r.status);
                    return [];
                } else {
                    console.log('Palvelin virhe! ' + r.status);
                    return [];
                }
            });
            setState({...state, data: data, loading: false});
        };
        runEffect();

        return () => {
        }
    }, [setState, query]);

    const show = ((state || {}).show || 10);
    const realData = ((state || {}).data || []);
    const data = realData.slice(0, show);

    const average = (data) => {
        const [s,c] = (data || []).reduce(([sum, count], row) => [sum + row[1], ++count], [0,0]);
        return c !== 0 ? Math.round((s/c) * 100) / 100: 0;
    };

    const rowToExcel = (row) => {
        return [moment(row[0]).format('LLL'), row[1], row[2], row[3]];
    };

    return (
        <Table celled padded>
            <Table.Header>
                <Table.Row>
                    <Table.HeaderCell colSpan='4'>
                        <Form onSubmit={event => Excel(realData.map(rowToExcel))}>
                            <Button icon labelPosition='left' disabled={(data || []).length === 0}>
                                <Icon name='download'/>
                                Lataa
                            </Button>
                        </Form>
                    </Table.HeaderCell>
                </Table.Row>
                <Table.Row>
                    <Table.HeaderCell>
                        <Label ribbon>Keskiarvo {average(realData)}</Label>
                        Arvosana
                    </Table.HeaderCell>
                    <Table.HeaderCell width={10}>Palaute</Table.HeaderCell>
                    <Table.HeaderCell>Aikaleima</Table.HeaderCell>
                    <Table.HeaderCell width={4}>Käyttäjäagentti</Table.HeaderCell>
                </Table.Row>
            </Table.Header>
            <Table.Body>
                {state.loading ? <Loader/> :<DataRows data={data}/>}
            </Table.Body>
            {!state.loading && realData.length !== data.length ?
                <ShowMore moreFn={() => setState({...state, show: (show + 500)})}/> : null}
        </Table>
    );
};

export default withRouter(Results)
