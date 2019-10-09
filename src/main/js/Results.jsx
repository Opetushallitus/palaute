import 'semantic-ui-css/semantic.min.css';
import React, {useState, useEffect} from "react";
import {withRouter} from "react-router";
import {Table, Form, Label, Button, Icon, Rating} from 'semantic-ui-react';
import Excel from './Excel';
import * as moment from 'moment';
import 'moment/locale/fi';

const Results = (props) => {
    const query = new URLSearchParams(props.location.search).get("q");
    const [data, setData] = useState();
    useEffect(() => {
        const controller = new AbortController();
        const runEffect = async () => {
            try {
                const data = await fetch(
                    "/palaute/api/palaute?q=" + query,
                    {signal: controller.signal}
                ).then(r => r.json());
                setData(data);
            } catch (err) {
                if (err.name === 'AbortError') {
                    console.log("Request was canceled via controller.abort");
                    return;
                }
            }
        };
        runEffect();

        return () => {
            controller.abort();
        }
    }, [setData, query]);

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
                        <Form onSubmit={event => Excel(data.map(rowToExcel))}>
                            <Button icon labelPosition='left' disabled={(data || []).length === 0}>
                                <Icon name='download'/>
                                Lataa
                            </Button>
                        </Form>
                    </Table.HeaderCell>
                </Table.Row>
                <Table.Row>
                    <Table.HeaderCell>
                        <Label ribbon>Keskiarvo {average(data)}</Label>
                        Arvosana
                    </Table.HeaderCell>
                    <Table.HeaderCell>Aikaleima</Table.HeaderCell>
                    <Table.HeaderCell>Käyttäjäagentti</Table.HeaderCell>
                    <Table.HeaderCell width={10}>Palaute</Table.HeaderCell>
                </Table.Row>
            </Table.Header>
            <Table.Body>
                {(data || []).map((row, index) => {
                    return <Table.Row key={index}>
                        <Table.Cell collapsing><Rating defaultRating={row[1]} maxRating={5} disabled/></Table.Cell>
                        <Table.Cell collapsing>{moment(row[0]).format('LLL')}</Table.Cell>
                        <Table.Cell collapsing>{row[2]}</Table.Cell>
                        <Table.Cell singleline>{row[3]}</Table.Cell>
                    </Table.Row>;
                })}
            </Table.Body>
        </Table>
    );
};

export default withRouter(Results)
