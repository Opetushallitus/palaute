import XLSX from 'xlsx';
import { saveAs } from 'file-saver';


const Excel = (ws_data) => {
    const wb = XLSX.utils.book_new();
    wb.Props = {
        Title: "Palaute",
        Subject: "Palaute",
        Author: "Palautepalvelu",
        CreatedDate: new Date()
    };
    wb.SheetNames.push("Test Sheet");
    wb.Sheets["Test Sheet"] = XLSX.utils.aoa_to_sheet(ws_data);
    const wbout = XLSX.write(wb, {bookType:'xlsx',  type: 'binary'});
    function s2ab(s) {
        var buf = new ArrayBuffer(s.length);
        var view = new Uint8Array(buf);
        for (var i=0; i<s.length; i++) view[i] = s.charCodeAt(i) & 0xFF;
        return buf;
    }
    saveAs(new Blob([s2ab(wbout)],{type:"application/octet-stream"}), 'palaute.xlsx');
};

export default Excel;
