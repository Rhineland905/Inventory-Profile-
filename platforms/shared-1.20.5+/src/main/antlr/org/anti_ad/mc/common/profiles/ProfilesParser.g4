parser grammar ProfilesParser;

options {
    tokenVocab=ProfilesLexer;
}

script
	:	profile* EOF
	;

profile : PROFILE Id (ACTIVATE activeSlotName)? slotsDef;
slotsDef : (slotDef)* ;
slotDef : slotname  (itemDef)*? ;
itemDef : itemName ('->' components )?;
itemName : NamespacedId ( customName )?;
customName: LPAREN STRING RPAREN;
components: LBRACK component (',' component)*? RBRACK;
component:  NamespacedId ( customName )?;
name: 'id' COLON NamespacedId;

slotname : (HOT1|HOT2|HOT3|HOT4|HOT5|HOT6|HOT7|HOT8|HOT9|CHESTPLATE|LEGS|FEET|HEAD|OFFHAND);
activeSlotName : (HOT1|HOT2|HOT3|HOT4|HOT5|HOT6|HOT7|HOT8|HOT9);
