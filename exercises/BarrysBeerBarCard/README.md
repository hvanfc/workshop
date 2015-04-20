BarrysBeerBarCard
==============================

In this exercise, we will demonstrate how to emulate an NFC card.
We will create an app where the user can specify a name for a card.
The card can then be read by the BarrysBeerBar app.
For this, changes are also required in BarrysBeerBar.



The exercise
--------------

In the code (as well as the xml files) you will find TODO's. Fix this code to make it run :)
TODOs in this exercise:
- CardService.java
- AndroidManifest.xml

In BarrysBeerBar you will need to:
- Update nfc_tech_filter.xml, you need to add an extra tech list for the IsoDep format. HCE only supports IsoDep.
- Add a method for reading IsoDep tags.
	- connect to the IsoDep tag
	- create a command ADPU, using the same AID that you will use in BarrysBeerBarCard
	- transceive the command
	- The result contains a statusword and a payload.
	- If the statusword equals the agreed bytes SELECT OK AKA {(byte) 0x90, (byte) 0x00}, then you can return the payload.
