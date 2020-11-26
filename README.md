# 2020_FTP-klijent-server-implementacija
Prenos fajlova izmedju klijenata i servera. Implementacija FTP protokola sa prosirenim funkcionalnostima kao na primer AES 128-bit enkripcija. Komunikacija se vrsi pomocu objekata 
koji se serijalizuju i enkriptuju, i ako je potrebno salje se ili preuzima fajl koji je takodje enkriptovan radi sprecavanja kradje podataka. Korisnicki interfejs je implementiran 
samo na klijentskoj strani, takodje sadrzi UNIX-style terminal za direktno izvrsavanje FTP komandi.
