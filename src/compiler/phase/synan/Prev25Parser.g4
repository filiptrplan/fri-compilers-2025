parser grammar Prev25Parser;

@header {

	package compiler.phase.synan;
	
	import java.util.*;
	import compiler.common.report.*;
	import compiler.phase.lexan.*;
	import compiler.phase.abstr.*;
}

@members {

	private Location loc(Token tok) { return new Location((LexAn.LocLogToken)tok); }
	private Location loc(Token     tok1, Token     tok2) { return new Location((LexAn.LocLogToken)tok1, (LexAn.LocLogToken)tok2); }
	private Location loc(Token     tok1, Locatable loc2) { return new Location((LexAn.LocLogToken)tok1, loc2); }
	private Location loc(Locatable loc1, Token     tok2) { return new Location(loc1, (LexAn.LocLogToken)tok2); }
	private Location loc(Locatable loc1, Locatable loc2) { return new Location(loc1, loc2); }

}

options{
    tokenVocab=Prev25Lexer;
}

// MISC
id_colon_type 
    : (IDENT COLON type) (COMMA IDENT COLON type)* ;

// PROGRAM
source returns [AST.Nodes<AST.FullDefn> ast]
	: ( defs += def )+ EOF { 
        ArrayList<AST.FullDefn> list = new ArrayList<AST.FullDefn>();
        for (DefContext d : $defs) {
            list.add(d.ast);
        }
        $ast = new AST.Nodes<AST.FullDefn>(list);
    }
	;

// DEFINITIONS
def returns [AST.FullDefn ast]
    : TYP IDENT EQ type { $ast = new AST.TypDefn(this.loc($TYP, $type.stop), $IDENT.text, $type.ast); }
    | VAR IDENT COLON type { $ast = null; }
    | FUN IDENT LPAREN (id_colon_type)? RPAREN COLON type { $ast = null; }
    | FUN IDENT LPAREN (id_colon_type)? RPAREN COLON type EQ stmts { $ast = null; }
    ;

// STATEMENTS
stmts 
    : stmt COMMA stmts
    | stmt
    ;

stmt 
    : expr
    | expr EQ expr
    | RETURN expr
    | WHILE expr DO stmts? END
    | IF expr THEN stmts? END
    | IF expr THEN stmts? ELSE stmts? END
    | LET def+ IN stmts END 
    ; 

// TYPES
type returns [AST.Type ast]
    : IDENT { $ast = null; }
    | INT{ $ast = null; }
    | CHAR{ $ast = null; }
    | BOOL { $ast = null; }
    | VOID{ $ast = null; }
    | IDENT{ $ast = null; }
    | LBRACKET INTCONST RBRACKET type{ $ast = null; }
    | POWER type{ $ast = null; }
    | LT id_colon_type GT{ $ast = null; }
    | LBRACE id_colon_type RBRACE{ $ast = null; }
    | LPAREN (type (COMMA type)*)? RPAREN COLON type{ $ast = null; }
    ;

// EXPRESSIONS
expr : or ;
or : or OR and | and ;
and : and AND comp | comp ;
comp 
    : add EQ_EQ add 
    | add NEQ add
    | add LT add
    | add GT add
    | add GTE add
    | add LTE add
    | add
    ;
add 
    : add PLUS mul 
    | add MINUS mul
    | mul 
    ;
mul
    : mul STAR prefix
    | mul SLASH prefix
    | mul PERCENT prefix
    | prefix
    ;
prefix
    : PLUS prefix
    | MINUS prefix
    | BANG prefix
    | POWER prefix
    | postfix
    ;
postfix
    : postfix LPAREN (expr (COMMA expr)*)? RPAREN
    | postfix LBRACKET expr RBRACKET
    | postfix POWER
    | postfix DOT IDENT
    | const
    ;
const
    : INTCONST
    | CHARCONST
    | STRCONST
    | TRUE
    | FALSE
    | NULL
    | SIZEOF type
    | IDENT
    | LPAREN expr RPAREN
    | LBRACE expr COLON type RBRACE
    ;
