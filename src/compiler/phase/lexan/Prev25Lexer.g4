lexer grammar Prev25Lexer;

@header {
	package compiler.phase.lexan;

	import compiler.common.report.*;
}

@members {
    @Override
	public LexAn.LocLogToken nextToken() {
		return (LexAn.LocLogToken) super.nextToken();
	}
}

// CONSTANTS

INTCONST    : (PLUS|MINUS)? [0-9]+ ;

fragment ESC_BACK    : '\\\\' ;
fragment ESC_QUOTE   : '\\\'' ;
fragment ESC_DQUOTE  : '\\"' ;
// The range here is all the ASCII chars from 0x20 to 0x7E except double quote and backslash
fragment EXCEPT_DQUOTE   : [\u0020-\u0021\u0023-\u005B\u005D-\u007E] ;
// The range here is all the ASCII chars from 0x20 to 0x7E except single quote and backslash
fragment EXCEPT_QUOTE    : [\u0020-\u0026\u0028-\u005B\u005D-\u007E] ;
fragment HEX_CHAR    : '\\0x' [0-9A-F][0-9A-F] ;

STRCONST    : '"' ( EXCEPT_DQUOTE | ESC_BACK | ESC_DQUOTE | HEX_CHAR )* '"' ; 
CHARCONST   : '\'' ( EXCEPT_QUOTE | ESC_BACK | ESC_QUOTE  | HEX_CHAR ) '\'' ;

// SYMBOLS

AND         : '&' ;
OR          : '|' ;
EQ_EQ       : '==' ;
NEQ         : '!=' ;
LT          : '<' ;
GT          : '>' ;
LTE         : '<=' ;
GTE         : '>=' ;
STAR        : '*' ;
SLASH       : '/' ;
PERCENT     : '%' ;
PLUS        : '+' ;
MINUS       : '-' ;
BANG        : '!' ;
DOT         : '.' ;
POWER       : '^' ;
EQ          : '=' ;
COMMA       : ',' ;
LBRACE      : '{' ;
RBRACE      : '}' ;
LPAREN      : '(' ;
RPAREN      : ')' ;
LBRACKET    : '[' ;
RBRACKET    : ']' ;
COLON       : ':' ;

// KEYWORDS

BOOL        : 'bool' ;
CHAR        : 'char' ;
DO          : 'do' ;
ELSE        : 'else' ;
END         : 'end' ;
FALSE       : 'false' ;
FUN         : 'fun' ;
IF          : 'if' ;
IN          : 'in' ;
INT         : 'int' ;
LET         : 'let' ;
NULL        : 'null' ;
RETURN      : 'return' ;
SIZEOF      : 'sizeof' ;
THEN        : 'then' ;
TRUE        : 'true' ;
TYP         : 'typ' ;
VAR         : 'var' ;
VOID        : 'void' ;
WHILE       : 'while' ;

// IDENTIFIER

IDENT       : [A-Za-z_][A-Za-z0-9_]* ;

// COMMENTS AND WHITESPACE

COMMENT : '#' ~[\r\n]* -> skip ;
// Escape space, CR, LF, HT
WHITESPACE  : [ \n\r\u0009]+ -> skip ;

